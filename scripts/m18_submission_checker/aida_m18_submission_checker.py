"""
aida_m18_submission_checker.py
June 21st, 2019

to run:
    python3 aida_m18_submission_checker.py <path/to/archive>
        or
    python aida_m18_submission_checker.py <path/to/archive>
"""
import sys
import os
import re
import logging
import tarfile
import zipfile

'''
Globals
'''
# list of names of items in the archive - to be populated after determining what filetype the archive is
ARCHIVE_NAMES = []

warnings_dict = {'1a' : ".ttl files must be located in the <run_ID>/NIST/ and optionally in the <run_id>/INTER-TA directory",
                 '1b' : ".ttl files must be located the appropriate hypothesis_id subdirectory of the <run_ID>/NIST/ directory. i.e. <run_id>/NIST/<hypothesis_id>/<document_id>.ttl",
                 '2' : ".ttl files must be located in the <run_ID>/NIST/ and optionally in the <run_id>/INTER-TA directory",
                 '3' : ".ttl files must be located in the <run_ID>/ directory" }


def check_runtime_args(args):
    """
    Checks the runtime arguments to ensure it is a file that exists. This function will log the errors detected, if any.
    :param args: runtime arguments (sys.argv)
    :return: True if valid runtime arguments, False otherwise
    """

    # check that an argument was given
    if len(args) < 2:
        logging.error("Missing archive file as argument.\nTo run: python3 aida_m18_submission_checker.py <path/to/archive>")
        return False

    # checks that argument is an existing file
    if not os.path.isfile(args[1]):
        logging.error("Argument is not a file.\nTo run: python3 aida_m18_submission_checker.py <path/to/archive>")
        return False

    return True

def get_valid_archive_filetype(archive_file_name):
    """
    Checks the archive file type. If archive is not of valid file type, log an error message.
    Valid filetypes: .tgz, .tar.gz, or .zip.

    :return: string with archive filetype 'tar' or 'zip', or None if invalid filetype
        (i.e. the library that should be used to read the archive)
    :rtype: string, or None
    """

    # note - dont need to check for multiple occurances of file extentions, this will cause the get_task_type to fail
    # with unkown task type.

    if archive_file_name.endswith('.tar.gz') or archive_file_name.endswith('.tgz'):
        return 'tar'
    elif archive_file_name.endswith('.zip'):
        return 'zip'
    else:
        logging.error("Archive filetype is unknown. Please use .zip, .tgz, or .tar.gz")
        return None

def get_archive_member_names(archive_file_name, archive_file_type):
    """
    Gets all of the file names/members of the archive.
    :param archive_file_name: the file name/path of the archive
    :param archive_file_type: the file extention type: 'tar' or 'zip'
    :return: a list of the names/members in the archive on success, None if failure to open/read archive
    :rtype: bool
    """

    logging.info("Checking archive... this may take a moment for large files.")

    try:

        if archive_file_type == 'zip':

            archive = zipfile.ZipFile(archive_file_name, 'r', allowZip64=True)
            return archive.namelist()

        elif archive_file_type == 'tar':

            archive = tarfile.open(archive_file_name, mode='r')
            archive_members = archive.getmembers()
            archive_name_list = []

            for item in archive_members:
                if item.type == tarfile.DIRTYPE:

                    #append a '/' to the end of the directory name to match zip output formatting
                    archive_name_list.append(item.name + '/')

                else:
                    archive_name_list.append(item.name)

            return archive_name_list

    except:

        # expected errors: (zipfile.BadZipFile, tarfile.ReadError), but catch all of them anyways
        logging.error("Error thrown attempting to read/open the archive. Please use 'zip' or 'tar' to create your archive.")

    return None

def get_task_type(archive_file_name, archive_member_names):
    """
    :param archive_member_names: a list of members/names in the archive
    :return: string corresponding to the task type, which could be any of the following: '1a', '1b', '2', or '3'
    :rtype: string
    """

    # count the number of .'s in the archive file name to determine the task type. Hope the user has the proper naming convention
    basename = os.path.basename(archive_file_name)

    dot_count = basename.count('.')

    # get rid of extra . count if the file extention is .tar.gz
    if basename.endswith('.tar.gz'):
        dot_count -= 1

    if dot_count == 1:
        # if theres only 1 dot_count, we need to check to see if it is 1a or 1b.
        logging.info("Based on the number of periods in the file name, this is either submission task type 1a OR 1b...")

        member_names_str = " ".join(archive_member_names)

        # if .ttl file in "/NIST/" directory then ttls_under_nist_dir is not none (possible 1a)
        ttls_under_nist_dir = re.compile(".*\/NIST\/[^\/]+.ttl").search(member_names_str)
        # if ".ttl file in a subdirectory of /NIST/" then ttls_under_nist_subdir is not none (possible 1b)
        ttls_under_nist_subdir = re.compile(".*\/NIST\/\S*\/\S*\.ttl").search(member_names_str)

        detected = 0
        if ttls_under_nist_dir is not None:
            detected += 1
        if ttls_under_nist_subdir is not None:
            detected += 2

        '''
        detected values:
        0 -- nothing detected       continue as 1a
        1 -- only 1a detected       continue as 1a
        2 -- only 1b detected       continue as 1b
        3 -- 1a and 1b detected     continue as 1b
        '''
        if detected == 0:
            logging.warning("No .ttl files found in a .../NIST/ directory, continuing to check as submission type 1a.")
            return '1a'
        if detected == 1:
            logging.info("Found .ttl files in a .../NIST/ directory, continuing to check as submission type 1a.")
            return '1a'
        elif detected == 2:
            logging.info("Found .ttl files in a subdirectory of ../NIST/, continuing to check as submission type 1b.")
            return '1b'
        elif detected == 3:
            logging.warning("Found .ttl files in a .../NIST/ directory and in a subdirectory of ../NIST/, continuing to check as submission type 1b.")
            return '1b'

    elif dot_count == 2:
        logging.info("Based on the number of periods in the file name, this archive is assumed to be a Task {0} submission.".format('2'))
        return '2'

    elif dot_count == 3:
        logging.info("Based on the number of periods in the file name, this archive is assumed to be a Task {0} submission.".format('3'))
        return '3'

    else:
        logging.error("Based on the number of periods in the file name, this is neither a Task 1a, 1b, 2, or 3 submission. Please name your submission file based on the rules defined in section 9 of the NIST AIDA 2019 Evaluation Plan.")
        return None


def get_archive_submit_ready_status_values(task_type, archive_member_names):
    """
    Prereq: ARCHIVE_FILE_NAME, and ARCHIVE_NAMES have been set

    :return: total number of ttl files found, a dict of ttl counts per valid directory, and a dict of ttl counts per invalid directory
    :rtype: int, dict {'dirname':int,...}, dict {'dirname':int,...}
    """
    ttl_valid_count_dict = {}
    ttl_invalid_count_dict = {}
    ttls_total_count = 0

    valid_dir_check = None

    # function pointer for which type of validity checking we want to do
    if task_type == '1a' or task_type == '2':
        valid_dir_check = do_TA1a_2_check
    elif task_type == '1b':
        valid_dir_check = do_TA1b_check
    elif task_type == '3':
        valid_dir_check = do_TA3_check

    for name in archive_member_names:

        # split into levels
        path_items = re.split('/', name)

        if name.endswith('.ttl'):

            ttls_total_count += 1
            file_dir_status = valid_dir_check(path_items)

            dir = os.path.dirname(name) + '/'

            if file_dir_status:
                #get value for the current ttl dir, if it dosen't exist then add it to the dict
                curr_val = ttl_valid_count_dict.setdefault(dir, 0)
                curr_val += 1
                ttl_valid_count_dict[dir] = curr_val

            else:
                #get value for the current ttl dir, if it dosen't exist then add it to the dict
                curr_val = ttl_invalid_count_dict.setdefault(dir, 0)
                curr_val += 1
                ttl_invalid_count_dict[dir] = curr_val

    return ttls_total_count, ttl_valid_count_dict, ttl_invalid_count_dict

def do_TA1a_2_check(path_items):
    """
    Check the path in the archive to ensure the path is valid
    TASK 1a Directory structure:
    <TA1aperformer>_<run>
        NIST
            <document_id>.ttl                  1a: (1 to X); 2: (1)
		INTER-TA
		    <document_id>.ttl                  1a: (0 to X); 2: (0 or 1)


    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :param path_items: The path to the .ttl file that needs to be checked
    :return: 'NIST' if .ttl file is in the ../NIST/ directory, 'INTER-TA' if in the ../INTER-TA/ directory or None if not in either directory
    :rtype: string or None
    """

    # validate that the ttl file is in an acceptable directory
    # rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
    # rule: ttl needs to be in directory named NIST, or INTER-TA

    try:
        if ((path_items[-2] == 'NIST' or path_items[-2] == 'INTER-TA') and len(path_items) == 3):
            return True
    except:
        return False
    return False


def do_TA1b_check(path_items):
    """
    Check the path in the archive to ensure the path is valid
    Task 1b directory structure:
    <TA1performer>_<run>
        NIST
            hypothesisID			(1 to Y)
                <document_id>.ttl 		(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :param path_items: The path to the .ttl file that needs to be checked
    :return: True if the file path is valid, False otherwise
    :rtype: bool
    """

    # validate that the ttl file is in an acceptable directory
    # rule: ttl needs to be in 3rd level -- aka 4th item in list['name', 'NIST', 'hypothesis', 'valid.ttl']
    # rule: ttl needs to be in a subdirectory within the NIST directory
    try:
        if path_items[-3] == 'NIST' and len(path_items) == 4:
            return True
    except:
        return False
    return False

def do_TA3_check(path_items):
    """
    Check the path in the archive to ensure the path is valid
    Task 3 directory structure:
    <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>
        <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>.<SIN ID>.<SIN frame ID>.<H followed by three digits>.ttl			(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    we do not check for all NIST standards set by the document, only for basic directory structure.
    :param path_items: The path to the .ttl file that needs to be checked
    :return: True if the file path is valid, False otherwise
    :rtype: bool
    """

    # validate that the ttl file is in an acceptable directory
    # rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
    # rule: ttl needs to be in directory named NIST, or INTER-TA

    return True if len(path_items) == 2 else False

def get_archive_status_and_log(archive_file_name, task_type, ttls_total_count, ttl_valid_count_dict, ttl_invalid_count_dict):
    """
    Handles the proper log formatting (info, warning, errors) and logs the output determining the status of the archive
    logs if archive check was Success, Partial Success, or Failure
    :param archive_file_name: name of archive
    :param task_type: task type of archive. 1a, 1b, 2, or 3
    :param ttls_total_count: total count of .ttl files in archive
    :param ttl_valid_count_dict: dictionary with counts of .ttl files per directory (e.g.: {runid/NIST/:20, runid/INTER-TA/:20})
    :param ttl_invalid_count_dict: dictionary with counts of .ttl files per directory (e.g.: {runid/invalid_dir/: 20, ...})
    :returns: True on Success or Partial success, False otherwise
    """

    logging.info("M18 Submission Checker Report for: \n\t{0}".format(archive_file_name))

    # get totals
    count_to_be_validated = sum(ttl_valid_count_dict.values())
    count_not_to_be_validated = sum(ttl_invalid_count_dict.values())

    row_format ="{:<{col_size}} | {:<6} | {:<17}"

    # print counts per each valid type directory
    if count_to_be_validated > 0:
        col_size = max(max(map(len, ttl_valid_count_dict)), len("Directory ")) + 3
        logging.info(row_format.format("   Directory", "# .ttl", "", col_size=col_size))
        for dir, count in ttl_valid_count_dict.items():
            logging.info(row_format.format(("   "+dir), count, "valid location", col_size=col_size))

    if count_not_to_be_validated > 0:
        col_size = max(max(map(len, ttl_invalid_count_dict)), len("Directory "))
        logging.warning(row_format.format("Directory", "# .ttl", "", col_size=col_size))
        for dir, count in ttl_invalid_count_dict.items():
            logging.warning(row_format.format(dir, count, "unexpected location, will not be checked", col_size=col_size))

    # log archive report
    logging.info("Total .ttl files found in submission archive: {0}".format(ttls_total_count))
    logging.info("Total number of .ttl files to be validated: {0}".format(count_to_be_validated))

    # if invalid ttls dont exist, dont bother logging this line
    if count_not_to_be_validated > 0:
        logging.info("Total number of .ttl files that will not be validated: {0}".format(count_not_to_be_validated))

    optional_flag = True if task_type == '1a' or task_type == '2' else False
    required_count = 0
    if optional_flag:
        #for task 1a and 2 specifically, if there are no required files we log that it is an error, else it is ok
        for k in ttl_valid_count_dict:
            #print("found {1} required files in {0}".format(k, str(ttl_valid_count_dict.get(k,0))))
            if '/NIST/' in str(k):
                required_count += ttl_valid_count_dict.get(k, 0)


    # SUCCESS -         when expected files exist and NO unexpected files exist
    # PARTIAL SUCCESS - when expected files exist and unexpected files exist
    # Failure -         All other cases
    if (optional_flag and required_count > 0) or ((not optional_flag) and count_to_be_validated > 0):
        if count_not_to_be_validated == 0:
            logging.info("SUCCESS! {0} is organized according to task {1} rules and is ready to be submitted!".format(archive_file_name, task_type))
            return True
        else:
            logging.info("For task {0}: {1}".format(task_type, warnings_dict[task_type]))
            logging.info("PARTIAL SUCCESS. {0} is organized according to task {1} rules and can be submitted, but there are .ttl files in unexpected locations that will not be validated.".format(archive_file_name, task_type))
            return True
    else:
        logging.info("For task {0}: {1}".format(task_type, warnings_dict[task_type]))
        logging.error("FAILURE! {0} is not organized according to task {1} rules, or has 0 files where 1 or more is expected.".format(archive_file_name, task_type))
        return False

def main():
    """
    :returns: True if script completed successfully (regardless if archive is submit ready or not)
              False if script ran into a fatal error during execution and did not complete successfully.
    :rtype: bool
    """

    # set logging to log/print INFO+ to stdout
    logging.basicConfig(level='INFO', format="%(levelname)s: %(message)s")

    if check_runtime_args(sys.argv) is False:
        return False

    archive_file_name = str(sys.argv[1])

    archive_file_type = get_valid_archive_filetype(archive_file_name)

    if archive_file_type is None:
        return False

    archive_member_names = get_archive_member_names(archive_file_name, archive_file_type)

    if archive_member_names is None:
        return False

    task_type = get_task_type(archive_file_name, archive_member_names)
    if task_type == None:
        return False

    ttls_total_count, ttl_valid_count_dict, ttl_invalid_count_dict = get_archive_submit_ready_status_values(task_type, archive_member_names)

    status = get_archive_status_and_log(archive_file_name, task_type, ttls_total_count, ttl_valid_count_dict, ttl_invalid_count_dict)

    return status


if __name__ == "__main__": main()
